package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.config.StorageProperties;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionDocumentoVersionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcesoProduccionDocumentoConsultaTest {
    private final ProcesoProduccionDocumentoVersionRepo documentos = mock(ProcesoProduccionDocumentoVersionRepo.class);
    private final ProcesoProduccionDocumentoStorage storage = mock(ProcesoProduccionDocumentoStorage.class);
    private final ProcesoProduccionDocumentoService target = new ProcesoProduccionDocumentoService(
            mock(ProcesoProduccionRepo.class), documentos, storage);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    @Test
    void ausenciaDeArchivoSeDevuelveDentroDeLaTransaccionSinRevertirla() {
        when(documentos.findByIdAndProcesoProcesoId(501L, 12)).thenReturn(Optional.of(documento()));
        when(storage.load("poe.docx"))
                .thenThrow(new ProcesoProduccionDocumentoStorage.ArchivoNoDisponibleException());
        ProcesoProduccionDocumentoService proxy = transactionalProxy();

        ProcesoProduccionDocumentoService.ConsultaDocumento result = proxy.consultarParaAnexo(12, 501L);

        assertThat(result.descarga()).isNull();
        assertThat(result.incidencia()).isInstanceOf(ProcesoProduccionDocumentoStorage.ArchivoNoDisponibleException.class);
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
    }

    @Test
    void versionAusenteEsIncidenciaSoloEnLaConsultaTolerante() {
        when(documentos.findByIdAndProcesoProcesoId(501L, 12)).thenReturn(Optional.empty());
        ProcesoProduccionDocumentoService proxy = transactionalProxy();
        assertThat(proxy.consultarParaAnexo(12, 501L).incidencia()).isInstanceOf(NoSuchElementException.class);
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
        assertThatThrownBy(() -> target.getDescarga(12, 501L)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void descargaIndividualConservaElErrorDeArchivoAusente() {
        when(documentos.findByIdAndProcesoProcesoId(501L, 12)).thenReturn(Optional.of(documento()));
        when(storage.load("poe.docx"))
                .thenThrow(new ProcesoProduccionDocumentoStorage.ArchivoNoDisponibleException());
        ProcesoProduccionDocumentoService proxy = transactionalProxy();

        assertThatThrownBy(() -> proxy.getDescarga(12, 501L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El archivo documental no esta disponible en el almacenamiento.");
        verify(transactions).rollback(any());
    }

    @Test
    void erroresDeBaseDeDatosSiguenSiendoErroresTransaccionales() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("BD no disponible");
        when(documentos.findByIdAndProcesoProcesoId(501L, 12)).thenThrow(failure);
        ProcesoProduccionDocumentoService proxy = transactionalProxy();

        assertThatThrownBy(() -> proxy.consultarParaAnexo(12, 501L)).isSameAs(failure);
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }

    @Test
    void noConvierteErroresInesperadosDeStorageEnIncidencias() {
        when(documentos.findByIdAndProcesoProcesoId(501L, 12)).thenReturn(Optional.of(documento()));
        IllegalStateException failure = new IllegalStateException("Error inesperado del proveedor");
        when(storage.load("poe.docx")).thenThrow(failure);
        assertThatThrownBy(() -> target.consultarParaAnexo(12, 501L)).isSameAs(failure);
    }

    @Test
    void configuracionDeProveedorInvalidaNoIntentaLeerElDisco() {
        ProcesoProduccionDocumentoVersion documento = documento();
        documento.setStorageProvider(null);
        when(documentos.findByIdAndProcesoProcesoId(501L, 12)).thenReturn(Optional.of(documento));
        assertThat(target.consultarParaAnexo(12, 501L).incidencia())
                .hasMessage("El proveedor de almacenamiento no esta soportado.");
        verifyNoInteractions(storage);
    }

    @Test
    void storageLocalClasificaArchivoAusenteYConservaProteccionDeRutas(@TempDir Path directory) {
        StorageProperties properties = mock(StorageProperties.class);
        when(properties.getUPLOAD_DIR()).thenReturn(directory.toString());
        LocalProcesoProduccionDocumentoStorage local = new LocalProcesoProduccionDocumentoStorage(properties);
        assertThatThrownBy(() -> local.load("no-existe.docx"))
                .isInstanceOf(ProcesoProduccionDocumentoStorage.ArchivoNoDisponibleException.class);
        assertThatThrownBy(() -> local.load("../fuera-del-directorio.docx"))
                .isInstanceOf(ProcesoProduccionDocumentoStorage.ClaveInvalidaException.class);
        assertThatThrownBy(() -> local.load(null))
                .isInstanceOf(ProcesoProduccionDocumentoStorage.ClaveInvalidaException.class);
    }

    private ProcesoProduccionDocumentoService transactionalProxy() {
        when(transactions.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (ProcesoProduccionDocumentoService) factory.getProxy();
    }

    private ProcesoProduccionDocumentoVersion documento() {
        ProcesoProduccionDocumentoVersion documento = new ProcesoProduccionDocumentoVersion();
        documento.setStorageProvider(ProcesoProduccionDocumentoVersion.StorageProvider.LOCAL_DISK);
        documento.setStorageKey("poe.docx");
        return documento;
    }
}
